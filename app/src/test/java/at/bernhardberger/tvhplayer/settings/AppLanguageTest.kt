package at.bernhardberger.tvhplayer.settings

import android.app.Application
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.BuildConfig
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppLanguageTest {
    private val choices = listOf("de-AT", "de-DE", "en", "fr").map(::AppLanguage)

    @Test
    fun pickerLists_theBuildsLanguages_withPseudoLocalesInDebug() {
        val pseudoLocales = if (BuildConfig.BUILD_TYPE == "debug") listOf("en-XA", "ar-XB") else emptyList()
        assertEquals(SHIPPED_TAGS + pseudoLocales, AppLanguage.shipped.map { it.languageTag })
        assertEquals(30, SHIPPED_TAGS.size)
        assertEquals(SHIPPED_TAGS.size, SHIPPED_TAGS.toSet().size)
    }

    @Test
    fun generatedLocaleConfig_namesExactlyThePickersLanguages() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val parser = context.resources.getXml(R.xml.locales_config)
        val names = mutableListOf<String>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                names += parser.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
            }
        }
        assertEquals(BuildConfig.APP_LANGUAGES.toList(), names)
    }

    @Test
    fun everyShippedEntry_hasItsOwnNativeName() {
        val real = AppLanguage.shipped.filter { it.languageTag in SHIPPED_TAGS }
        val names = real.map { it.nativeName() }
        assertTrue(names.all { it.isNotBlank() })
        assertEquals("Entries must be told apart in the picker: $names", names.size, names.toSet().size)
        assertEquals("עברית", AppLanguage("he").nativeName())
        assertEquals("العربية", AppLanguage("ar").nativeName())
        assertEquals("日本語", AppLanguage("ja").nativeName())
        assertEquals("한국어", AppLanguage("ko").nativeName())
        assertEquals("English (United Kingdom)", AppLanguage("en-GB").nativeName())
        assertEquals("Português (Portugal)", AppLanguage("pt-PT").nativeName())
        assertEquals("Português (Brasil)", AppLanguage("pt-BR").nativeName())
    }

    @Test
    fun scriptAndHebrewEntries_takeTheirUsualRegion_andNeverTheSystemsOtherScript() {
        assertEquals("he-IL", AppLanguage("he").applicationLocaleTag(Locale.US))
        assertEquals("ar-EG", AppLanguage("ar").applicationLocaleTag(Locale.US))
        assertEquals("zh-Hant-TW", AppLanguage("zh-Hant").applicationLocaleTag(Locale.US))
        assertEquals("zh-Hant-TW", AppLanguage("zh-Hant").applicationLocaleTag(Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals("zh-Hant-HK", AppLanguage("zh-Hant").applicationLocaleTag(Locale.forLanguageTag("zh-Hant-HK")))
        assertEquals("zh-Hans-CN", AppLanguage("zh-Hans").applicationLocaleTag(Locale.forLanguageTag("zh-Hant-TW")))
        assertEquals("nb-NO", AppLanguage("nb").applicationLocaleTag(Locale.US))
        assertEquals("pt-BR", AppLanguage("pt-BR").applicationLocaleTag(Locale.forLanguageTag("pt-PT")))
    }

    @Test
    fun storedScriptRegionAndLegacyHebrewLocales_mapToTheShippedEntries() {
        val shipped = SHIPPED_TAGS.map(::AppLanguage)
        assertEquals(AppLanguage("zh-Hant"), AppLanguage.fromLanguageTags("zh-Hant-HK", shipped))
        assertEquals(AppLanguage("zh-Hant"), AppLanguage.fromLanguageTags("zh-Hant-TW", shipped))
        assertEquals(AppLanguage("zh-Hans"), AppLanguage.fromLanguageTags("zh-Hans-CN", shipped))
        assertEquals(AppLanguage("zh-Hans"), AppLanguage.fromLanguageTags("zh-CN", shipped))
        assertEquals(AppLanguage("zh-Hant"), AppLanguage.fromLanguageTags("zh-TW", shipped))
        assertEquals(AppLanguage("he"), AppLanguage.fromLanguageTags("he-IL", shipped))
        // Whatever tag the platform stores for the Hebrew application locale.
        assertEquals(AppLanguage("he"), AppLanguage.fromLanguageTags(Locale.forLanguageTag("he-IL").toLanguageTag(), shipped))
        assertEquals(AppLanguage("he"), AppLanguage.fromLanguageTags(Locale("iw", "IL").toLanguageTag(), shipped))
        assertEquals(AppLanguage("ar"), AppLanguage.fromLanguageTags("ar-SA", shipped))
        assertEquals(AppLanguage("pt-PT"), AppLanguage.fromLanguageTags("pt-PT", shipped))
        assertEquals(AppLanguage("pt-BR"), AppLanguage.fromLanguageTags("pt-BR,en", shipped))
        assertEquals(AppLanguage("de-CH"), AppLanguage.fromLanguageTags("de-CH", shipped))
        assertEquals(AppLanguage("en-GB"), AppLanguage.fromLanguageTags("en-GB", shipped))
        assertEquals(AppLanguage("en-US"), AppLanguage.fromLanguageTags("en-US", shipped))
        // A region the picker has no entry for stays its own entry rather than guessing a neighbour.
        assertEquals(AppLanguage("en-AU"), AppLanguage.fromLanguageTags("en-AU", shipped))
        assertEquals(AppLanguage("pt-AO"), AppLanguage.fromLanguageTags("pt-AO", shipped))
    }

    @Test
    fun regionalEntries_resolveToTheirLanguagesResources() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun clearCache(tag: String) = app.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) },
        ).getString(R.string.clear_cache)
        val german = clearCache("de")
        assertNotEquals(clearCache("en"), german)
        // de-CH and en-GB override only some strings; the rest resolves to the language.
        listOf("de-DE", "de-AT", "de-CH").forEach { assertEquals(it, german, clearCache(it)) }
        listOf("en-GB", "en-US").forEach { assertEquals(it, clearCache("en"), clearCache(it)) }
    }

    @Test
    fun machineTranslatedNotice_isOnlyFlaggedForTheMachineDraftedLanguages() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun flagged(tag: String) = app.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) },
        ).resources.getBoolean(R.bool.language_machine_translated)
        val machine = listOf(
            "ar", "cs", "da", "el", "es", "fi", "fr", "he", "hr", "hu", "it", "ja", "ko", "nb", "nl", "pl",
            "pt-BR", "pt-PT", "ro", "ru", "sv", "tr", "uk", "zh-Hans", "zh-Hant",
        )
        assertEquals(25, machine.size)
        // The two regional overrides are machine-drafted too, so they carry the notice themselves.
        (machine + listOf("en-GB", "de-CH")).forEach { assertTrue("$it carries the notice", flagged(it)) }
        // Source English, the reviewed German and its inherited regions carry none.
        listOf("en", "en-US", "de", "de-AT", "de-DE").forEach { assertFalse("$it has no notice", flagged(it)) }
    }

    @Test
    fun integratedResources_distinguishScriptsLegacyHebrewAndRegions() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        fun text(tag: String, id: Int = R.string.clear_cache) = app.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) },
        ).getString(id)
        val english = text("en")
        // Chinese scripts resolve to their own folder, whatever the region.
        assertNotEquals(text("zh-Hans"), text("zh-Hant"))
        assertEquals(text("zh-Hans"), text("zh-Hans-CN"))
        assertEquals(text("zh-Hant"), text("zh-Hant-TW"))
        assertEquals(text("zh-Hant"), text("zh-Hant-HK"))
        listOf("zh-Hans", "zh-Hant", "ja", "ko").forEach { assertNotEquals("$it must not fall back to English", english, text(it)) }
        // Hebrew resources live in the legacy iw folder; the modern and legacy codes meet the same strings.
        val hebrew = text("he")
        assertNotEquals(english, hebrew)
        assertEquals(hebrew, text("he-IL"))
        assertEquals(hebrew, app.createConfigurationContext(
            Configuration().apply { setLocales(LocaleList(Locale("iw", "IL"))) },
        ).getString(R.string.clear_cache))
        // Portuguese keeps its two regions apart from each other and from English.
        assertNotEquals(text("pt-BR"), english)
        assertNotEquals(text("pt-PT"), english)
        // A regional override carries only what differs: its other strings fall back to the language.
        assertNotEquals(text("en", R.string.no_epg), text("en-GB", R.string.no_epg))
        assertEquals(english, text("en-GB"))
        assertEquals(english, text("en-US"))
        assertNotEquals(text("de", R.string.close), text("de-CH", R.string.close))
        assertEquals(text("de"), text("de-CH"))
        // de-AT and de-DE have no override and inherit the reviewed German.
        listOf("de-AT", "de-DE").forEach {
            assertEquals(it, text("de"), text(it))
            assertEquals(it, text("de", R.string.close), text(it, R.string.close))
        }
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

    private companion object {
        val SHIPPED_TAGS = listOf(
            "cs", "da", "de-DE", "de-AT", "de-CH", "en-GB", "en-US", "es", "fr", "hr",
            "it", "hu", "nl", "nb", "pl", "pt-BR", "pt-PT", "ro", "fi", "sv", "tr",
            "el", "ru", "uk", "he", "ar", "zh-Hans", "zh-Hant", "ja", "ko",
        )
    }
}
