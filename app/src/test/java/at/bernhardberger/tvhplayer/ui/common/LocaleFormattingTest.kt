package at.bernhardberger.tvhplayer.ui.common

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocaleFormattingTest {
    private val date = LocalDate.of(2026, 3, 12)

    private fun inSelectedLocales(assertions: (Locale) -> Unit) {
        val previous = Locale.getDefault()
        try {
            for (locale in listOf(Locale.US, Locale.GERMAN)) {
                Locale.setDefault(if (locale == Locale.US) Locale.GERMAN else Locale.US)
                assertions(locale)
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun dayHeaderUsesSelectedLocaleOrderAndPunctuation() = inSelectedLocales { locale ->
        assertEquals(if (locale == Locale.US) "Thursday, March 12" else "Donnerstag, 12. März",
            formatDayHeader(date, locale))
    }

    @Test fun firstAiredRetainsLabeledMediumDate() = inSelectedLocales { locale ->
        assertEquals(if (locale == Locale.US) "Mar 12, 2026" else "12.03.2026",
            formatFirstAired(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), locale))
    }

    @Test fun unlabeledDateRemainsShortAndNumeric() = inSelectedLocales { locale ->
        assertEquals(if (locale == Locale.US) "3/12/2026" else "12.3.2026", formatShortDate(date, locale))
    }

    @Test fun fileSizesUseConfiguredContextRatherThanProcessLocale() = inSelectedLocales { locale ->
        val application = ApplicationProvider.getApplicationContext<Application>()
        val context = application.createConfigurationContext(Configuration(application.resources.configuration).apply {
            setLocale(locale)
        })
        val decimal = if (locale == Locale.US) "." else ","
        val space = if (locale == Locale.US) " " else "\u00a0"
        assertEquals(
            listOf("0${space}B", "500${space}B", "1${decimal}5 kB", "1${decimal}5 MB", "1${decimal}5${space}GB", "1${decimal}5 TB"),
            listOf(0L, 500L, 1_500L, 1_500_000L, 1_500_000_000L, 1_500_000_000_000L).map { formatFileSize(it, context) },
        )
    }

    @Test fun decimalsRetainResourceOwnedUnitsAndPrecision() = inSelectedLocales { locale ->
        assertEquals(if (locale == Locale.US) "12.5" else "12,5", formatDecimal(12.5, locale, 1, 1))
        assertEquals(if (locale == Locale.US) "12.3" else "12,3", formatDecimal(12.25, locale, 1, 1))
        assertEquals(if (locale == Locale.US) "0.0" else "0,0", formatDecimal(0.0, locale, 1, 1))
        assertEquals(if (locale == Locale.US) "0.5" else "0,5", formatDecimal(0.5, locale, 0, 1))
        assertEquals("1", formatDecimal(1.0, locale, 0, 1))
        assertEquals(if (locale == Locale.US) "1234.5" else "1234,5", formatDecimal(1234.5, locale, 1, 1))
    }

    @Test fun metadataNumbersDoNotGroupYearsOrEpisodeOrdinals() = inSelectedLocales { locale ->
        assertEquals("2026", formatMetadataNumber(2026, locale))
        assertEquals("1234", formatMetadataNumber(1234, locale))
        assertEquals("4", formatMetadataNumber(4, locale))
    }
}
