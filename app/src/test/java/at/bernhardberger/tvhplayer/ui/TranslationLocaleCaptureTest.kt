package at.bernhardberger.tvhplayer.ui

import android.app.Application
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.View
import at.bernhardberger.tvhplayer.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Shared proof that a capture really ran on Android's generated pseudo-locale resources: the
 * lanes of the other evidence tests call [PseudoLocaleEvidence] before they save a picture.
 */
internal object PseudoLocaleEvidence {
    const val EN_XA = "en-XA"
    const val AR_XB = "ar-XB"

    /** Resource qualifiers for the 960x540dp lanes: the pseudo-locale in place of `en`. */
    const val QUALIFIERS_EN_XA = "en-rXA-w960dp-h540dp-land-mdpi"
    const val QUALIFIERS_AR_XB = "ar-rXB-w960dp-h540dp-land-mdpi"
    /** Representative CJK entry for after the zh-Hant resources are integrated. */
    const val ZH_HANT = "zh-Hant-TW"
    const val QUALIFIERS_ZH_HANT = "b+zh+Hant+TW-w960dp-h540dp-land-mdpi"
    const val QUALIFIERS_EN_XA_XHDPI = "en-rXA-w960dp-h540dp-land-xhdpi"
    const val QUALIFIERS_AR_XB_XHDPI = "ar-rXB-w960dp-h540dp-land-xhdpi"

    private val probes = listOf(R.string.clear_cache, R.string.connection_try_again, R.string.setup_connection)

    private fun english(application: Application, id: Int) = application.createConfigurationContext(
        Configuration().apply { setLocale(Locale.ENGLISH) },
    ).getString(id)

    /** An integrated real translation is the active locale, left to right, and differs from English for every probe. */
    fun assertLocalized(tag: String, view: View? = null) {
        val application = RuntimeEnvironment.getApplication()
        assertEquals(tag, application.resources.configuration.locales[0].toLanguageTag())
        assertEquals(View.LAYOUT_DIRECTION_LTR, application.resources.configuration.layoutDirection)
        if (view != null) assertEquals(tag, view.resources.configuration.locales[0].toLanguageTag())
        probes.forEach { id ->
            val plain = english(application, id)
            val localized = application.getString(id)
            assertNotEquals("$tag must not fall back to English for ${application.resources.getResourceEntryName(id)}", plain, localized)
            assertTrue("$tag is a real translation, not a pseudo-locale: $localized", !localized.startsWith("[") && localized.none { it == '‏' || it == '‮' })
        }
    }

    /** The pseudo-locale is the active locale, differs from English, and carries its direction. */
    fun assertGenerated(tag: String, view: View? = null) {
        val application = RuntimeEnvironment.getApplication()
        val configuration = application.resources.configuration
        assertEquals(tag, configuration.locales[0].toLanguageTag())
        val rtl = tag == AR_XB
        val direction = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        assertEquals(direction, configuration.layoutDirection)
        if (view != null) {
            assertEquals(tag, view.resources.configuration.locales[0].toLanguageTag())
            assertEquals(direction, view.layoutDirection)
        }
        probes.forEach { id ->
            val plain = english(application, id)
            val generated = application.getString(id)
            assertNotEquals("$tag must not fall back to English for ${application.resources.getResourceEntryName(id)}", plain, generated)
            if (tag == EN_XA) {
                assertTrue("en-XA expands and brackets '$plain': $generated", generated.startsWith("[") && generated.endsWith("]"))
                assertTrue(generated.length > plain.length)
            } else {
                // ar-XB mirrors English letters and wraps them in right-to-left marks.
                assertTrue("ar-XB marks '$plain' right-to-left: ${generated.map { it.code.toString(16) }}", generated.any { it == '‏' || it == '‮' })
            }
        }
    }

    private val captureRoot = java.io.File("../artifacts/translations/round-030/captures")

    /**
     * Called by the existing capture lanes after they save a picture: when the lane ran on a
     * pseudo-locale, proves the generated resources and direction and keeps the picture with its
     * metadata under the ignored translations evidence directory. Other locales are untouched.
     */
    fun mirror(lane: String, name: String, bitmap: Bitmap, view: View?, fontScale: Float, density: String, focus: String, scenario: String) {
        val tag = RuntimeEnvironment.getApplication().resources.configuration.locales[0].toLanguageTag()
        val pseudo = tag == EN_XA || tag == AR_XB
        if (!pseudo && tag != ZH_HANT) return
        if (pseudo) assertGenerated(tag, view) else assertLocalized(tag, view)
        val directory = java.io.File(captureRoot, lane).apply { mkdirs() }
        val file = if (name.contains(tag)) name else "$tag-font$fontScale-$name"
        java.io.File(directory, "$file.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        java.io.File(directory, "$file.txt").writeText(
            metadata(tag, "960x540 logical; pixels=${bitmap.width}x${bitmap.height}", density, fontScale, focus, scenario, view, pseudo),
        )
    }

    /** Capture metadata lines shared by the pseudo-locale lanes. */
    fun metadata(tag: String, canvas: String, density: String, fontScale: Float, focus: String, scenario: String, view: View? = null, pseudo: Boolean = true): String {
        val application = RuntimeEnvironment.getApplication()
        return "canvas=$canvas; density=$density; locale=$tag; " +
            "layoutDirection=${if (application.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL) "RTL" else "LTR"}" +
            (view?.let { "; viewLayoutDirection=${if (it.layoutDirection == View.LAYOUT_DIRECTION_RTL) "RTL" else "LTR"}" } ?: "") +
            "; fontScale=$fontScale; focus=$focus; scenario=$scenario; " +
            (if (pseudo) "resources=Android generated pseudo-locale (aapt2); " else "resources=integrated app translation (real strings, machine-drafted; not a pseudo-locale); ") +
            "fixture metadata is English fake data; " +
            "static offline production composition only\n"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class TranslationLocaleCaptureTest {
    @Test
    @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_EN_XA)
    fun englishPseudoLocale_isGeneratedLongTextLeftToRight() = PseudoLocaleEvidence.assertGenerated(PseudoLocaleEvidence.EN_XA)

    @Test
    @Config(qualifiers = PseudoLocaleEvidence.QUALIFIERS_AR_XB)
    fun arabicPseudoLocale_isGeneratedMirroredRightToLeft() = PseudoLocaleEvidence.assertGenerated(PseudoLocaleEvidence.AR_XB)
}
