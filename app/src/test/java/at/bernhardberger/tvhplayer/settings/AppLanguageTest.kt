package at.bernhardberger.tvhplayer.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun chooserLanguages_mapToAndroidLanguageTags() {
        assertEquals(
            listOf("", "de", "en"),
            AppLanguage.entries.map { it.languageTag },
        )
    }

    @Test
    fun currentLanguageTag_mapsToChooserLanguage() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags(""))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTags("de"))
        assertEquals(AppLanguage.GERMAN, AppLanguage.fromLanguageTags("de-DE"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-GB"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("fr-FR"))
    }
}
