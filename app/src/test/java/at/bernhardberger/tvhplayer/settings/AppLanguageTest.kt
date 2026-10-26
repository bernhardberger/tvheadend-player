package at.bernhardberger.tvhplayer.settings

import android.app.Application
import at.bernhardberger.tvhplayer.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLanguageTest {
    private val choices = listOf("de-AT", "de-DE", "en", "fr").map(::AppLanguage)

    @Test
    fun pickerLists_theBuildsLanguages_withPseudoLocalesInDebug() {
        val pseudoLocales = if (BuildConfig.BUILD_TYPE == "debug") listOf("en-XA", "ar-XB") else emptyList()
        assertEquals(listOf("de", "en") + pseudoLocales, AppLanguage.shipped.map { it.languageTag })
    }

    @Test
    fun entries_areNamedInTheirOwnLanguage() {
        assertEquals("Deutsch", AppLanguage("de").nativeName())
        assertEquals("Français", AppLanguage("fr").nativeName())
        assertEquals("Deutsch (Österreich)", AppLanguage("de-AT").nativeName())
    }

    @Test
    fun bareLanguage_keepsTheSystemRegion_whenTheSystemSpeaksIt() {
        assertEquals("fr-CA", AppLanguage("fr").applicationLocaleTag(Locale.CANADA_FRENCH))
        assertEquals("en-GB", AppLanguage("en").applicationLocaleTag(Locale.UK))
    }

    @Test
    fun bareLanguage_takesItsUsualRegion_otherwise() {
        assertEquals("fr-FR", AppLanguage("fr").applicationLocaleTag(Locale.US))
        assertEquals("de-DE", AppLanguage("de").applicationLocaleTag(Locale.US))
        assertEquals("zh-Hans-CN", AppLanguage("zh-Hans").applicationLocaleTag(Locale.forLanguageTag("zh-Hant-TW")))
    }

    @Test
    fun regionalEntry_andFollowSystem_applyAsTheyAre() {
        assertEquals("de-AT", AppLanguage("de-AT").applicationLocaleTag(Locale.GERMANY))
        assertEquals("", AppLanguage.SYSTEM.applicationLocaleTag(Locale.GERMANY))
    }

    @Test
    fun storedLocales_mapToThePickerEntry() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("", choices))
        assertEquals(AppLanguage("de-AT"), AppLanguage.fromLanguageTags("de-AT", choices))
        // A bare tag stored by an earlier picker maps to its usual region.
        assertEquals(AppLanguage("de-DE"), AppLanguage.fromLanguageTags("de", choices))
        assertEquals(AppLanguage("en"), AppLanguage.fromLanguageTags("en-GB,de", choices))
        assertEquals(AppLanguage("fr"), AppLanguage.fromLanguageTags("fr-CA", choices))
    }

    @Test
    fun storedLocaleNoLongerListed_staysItsOwnEntry_soFollowSystemCanClearIt() {
        assertEquals(AppLanguage("de-CH"), AppLanguage.fromLanguageTags("de-CH", choices))
        assertEquals(AppLanguage("it-IT"), AppLanguage.fromLanguageTags("it-IT,en", choices))
        assertNotEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTags("it-IT", choices))
    }
}
