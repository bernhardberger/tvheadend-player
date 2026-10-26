package at.bernhardberger.tvhplayer.settings

import android.icu.text.DisplayContext
import android.icu.text.LocaleDisplayNames
import android.icu.util.ULocale
import at.bernhardberger.tvhplayer.BuildConfig
import java.util.Locale

/**
 * One entry of the in-app language picker. An empty [languageTag] follows the system. A bare
 * language ("fr") keeps the system's region when the system speaks that language and otherwise
 * takes the language's usual region; a tag with a region ("de-AT") is applied as it is.
 */
@JvmInline
value class AppLanguage(val languageTag: String) {
    val followsSystem: Boolean get() = languageTag.isEmpty()

    private val locale: ULocale get() = ULocale.forLanguageTag(languageTag)
    private val hasRegion: Boolean get() = locale.country.isNotEmpty()

    /** The language's name in itself, capitalized for a menu: "Deutsch", "Français", "English (United Kingdom)". */
    fun nativeName(): String = LocaleDisplayNames
        .getInstance(locale, DisplayContext.CAPITALIZATION_FOR_UI_LIST_OR_MENU)
        .localeDisplayName(locale)
        // Older ICU data lacks menu capitalization for some languages ("français").
        .replaceFirstChar { it.titlecase(locale.toLocale()) }

    /** The application locale to apply for this entry on a system whose first locale is [system]. */
    fun applicationLocaleTag(system: Locale): String = when {
        followsSystem || hasRegion -> languageTag
        sameLanguage(ULocale.addLikelySubtags(ULocale.forLocale(system))) -> system.toLanguageTag()
        else -> ULocale.Builder().setLocale(locale)
            .setRegion(ULocale.addLikelySubtags(locale).country).build().toLanguageTag()
    }

    private fun sameLanguage(maximized: ULocale): Boolean {
        val own = ULocale.addLikelySubtags(locale)
        return own.language == maximized.language && own.script == maximized.script
    }

    companion object {
        val SYSTEM = AppLanguage("")

        /** The shipped languages from the build's list, in picker order. */
        val shipped: List<AppLanguage> = BuildConfig.APP_LANGUAGES.map(::AppLanguage)

        /**
         * The picker entry for the stored application locales: [SYSTEM] when none is stored, and the stored
         * locale itself when none of [choices] matches it, such as a language a later build no longer ships.
         */
        fun fromLanguageTags(languageTags: String, choices: List<AppLanguage> = shipped): AppLanguage {
            val first = languageTags.substringBefore(',')
            if (first.isEmpty()) return SYSTEM
            val stored = ULocale.addLikelySubtags(ULocale.forLanguageTag(first))
            return choices.firstOrNull { it.hasRegion && ULocale.addLikelySubtags(it.locale).sameRegionAs(stored) }
                ?: choices.firstOrNull { !it.followsSystem && !it.hasRegion && it.sameLanguage(stored) }
                ?: AppLanguage(first)
        }

        private fun ULocale.sameRegionAs(other: ULocale) =
            language == other.language && script == other.script && country == other.country
    }
}
