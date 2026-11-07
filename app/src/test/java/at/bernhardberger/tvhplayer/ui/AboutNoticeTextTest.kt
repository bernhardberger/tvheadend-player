package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.screens.settings.aboutNoticeText
import at.bernhardberger.tvhplayer.ui.screens.settings.newestChangelogSections
import at.bernhardberger.tvhplayer.ui.screens.settings.changelogBlocks
import at.bernhardberger.tvhplayer.ui.screens.settings.ChangelogBlock
import at.bernhardberger.tvhplayer.ui.screens.settings.ChangelogBlockKind.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutNoticeTextTest {
    @Test fun trimsToFiveVersionsIncludingUnreleased() {
        val sections = listOf("Unreleased", "5", "4", "3", "2", "1").map { "## $it\n\n### Settings\n\n- Change $it.\n" }
        assertEquals(sections.take(5).joinToString("\n").trim(),
            newestChangelogSections("# Changelog\n\n" + sections.joinToString("\n")))
        assertEquals(sections.take(2).joinToString("\n").trim(),
            newestChangelogSections("# Changelog\n\n" + sections.take(2).joinToString("\n")))
        assertEquals("", newestChangelogSections("# Changelog"))
    }

    @Test fun rendersChangelogHeadingsAndJoinsBulletContinuations() {
        val excerpt = "## 0.4.10\n\n### Recordings\n\n- The *timeline* shows `ad breaks`\n  and chapters (#8),\n  with marks.\n- Next change.\n"
        assertEquals(listOf(
            ChangelogBlock(VERSION_HEADING, "0.4.10"),
            ChangelogBlock(GROUP_HEADING, "Recordings"),
            ChangelogBlock(BULLET_PARAGRAPH, "• The timeline shows ad breaks and chapters (#8), with marks."),
            ChangelogBlock(BULLET_PARAGRAPH, "• Next change."),
        ), changelogBlocks(excerpt))
    }

    @Test fun structuredChangelogKeepsFiveSectionsAndParagraphBoundaries() {
        val sections = listOf("Unreleased", "5", "4", "3", "2", "1").joinToString("\n\n") {
            "## $it\n\n### Settings\n\n- First bullet.\n  Wrapped line.\n\n- Second bullet.\n\nPlain paragraph.\nWrapped text."
        }
        val blocks = changelogBlocks("# Changelog\n\n$sections")
        assertEquals(listOf("Unreleased", "5", "4", "3", "2"),
            blocks.filter { it.kind == VERSION_HEADING }.map { it.text })
        assertEquals(5, blocks.count { it.kind == GROUP_HEADING })
        assertEquals(10, blocks.count { it.kind == BULLET_PARAGRAPH })
        assertEquals(ChangelogBlock(PARAGRAPH, "Plain paragraph. Wrapped text."), blocks.last())
        assertEquals(emptyList<ChangelogBlock>(), changelogBlocks("# Changelog"))
    }

    @Test fun convertsNoticeExcerptWithoutLosingAttributionOrLinks() {
        val markdown = """
            # Notices and attribution

            Tvheadend Player is an independently developed GPLv3 application. It is
            derived from [Preclikos/tvhstream](https://github.com/Preclikos/tvhstream) and
            retains that project's Git history, copyright, and GNU GPL v3 licensing.
            ## Libraries
            - **AndroidX** and *Jetpack Compose*
            * `Kotlin` coroutines
            + [https://www.gnu.org/licenses/](https://www.gnu.org/licenses/)
            1. See `NOTICE.md`.
        """.trimIndent()
        assertEquals("""
            Notices and attribution

            Tvheadend Player is an independently developed GPLv3 application. It is
            derived from Preclikos/tvhstream (https://github.com/Preclikos/tvhstream) and
            retains that project's Git history, copyright, and GNU GPL v3 licensing.

            Libraries

            • AndroidX and Jetpack Compose
            • Kotlin coroutines
            • https://www.gnu.org/licenses/
            • See NOTICE.md.
        """.trimIndent(), aboutNoticeText(markdown))
    }

    @Test fun preservesPlainTextAndParagraphBreaks() {
        val text = "GNU GENERAL PUBLIC LICENSE\nVersion 3, 29 June 2007\n\nCopyright (C) 2007 Free Software Foundation, Inc."
        assertEquals(text, aboutNoticeText(text))
        assertEquals("", aboutNoticeText("\n\n"))
    }
}
