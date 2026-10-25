package at.bernhardberger.tvhplayer.ui

import at.bernhardberger.tvhplayer.ui.screens.settings.aboutNoticeText
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutNoticeTextTest {
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
