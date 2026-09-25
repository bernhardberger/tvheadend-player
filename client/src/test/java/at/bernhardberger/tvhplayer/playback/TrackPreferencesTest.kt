package at.bernhardberger.tvhplayer.playback

import android.content.Context
import androidx.media3.common.*
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.test.core.app.ApplicationProvider
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val defaults get() = TrackSelectionParameters.DEFAULT

    @Test fun mappingPreservesOverridesDisabledTypesAndUnownedConstraints() {
        val group = TrackGroup(format("override", "en", MimeTypes.AUDIO_AC3))
        val current = defaults.buildUpon().addOverride(TrackSelectionOverride(group, listOf(0)))
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .setMaxAudioChannelCount(8).setMaxVideoSize(640, 480).build()
        val mapped = trackPreferences(current, PlayerSettings(listOf("de", "en"), "fr", AudioFormatPreference.PREFER_DOLBY, true))
        assertEquals(listOf("de", "en"), mapped.preferredAudioLanguages)
        assertEquals(listOf(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3), mapped.preferredAudioMimeTypes)
        assertEquals(C.ROLE_FLAG_DESCRIBES_VIDEO, mapped.preferredAudioRoleFlags)
        assertEquals(listOf("fr"), mapped.preferredTextLanguages)
        assertEquals(current.overrides, mapped.overrides)
        assertEquals(current.disabledTrackTypes, mapped.disabledTrackTypes)
        assertEquals(8, mapped.maxAudioChannelCount)
        assertEquals(640, mapped.maxVideoWidth)
    }

    @Test fun automaticRestoresCaptionManagerDelegationAndClearsOwnedAudioPreferences() {
        val prior = defaults.buildUpon().setPreferredTextLanguage("de")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION).build()
        val current = trackPreferences(prior, PlayerSettings(listOf("en"), "fr", AudioFormatPreference.PREFER_STEREO, true))
        assertEquals(listOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_MPEG), current.preferredAudioMimeTypes)
        assertFalse(current.usePreferredTextLanguagesAndRoleFlagsFromCaptioningManager)
        assertEquals(listOf("fr"), current.preferredTextLanguages)
        assertEquals(0, current.preferredTextRoleFlags)
        val restored = trackPreferences(current, PlayerSettings())
        assertTrue(restored.preferredAudioLanguages.isEmpty())
        assertTrue(restored.preferredAudioMimeTypes.isEmpty())
        assertEquals(0, restored.preferredAudioRoleFlags)
        assertTrue(restored.usePreferredTextLanguagesAndRoleFlagsFromCaptioningManager)
    }

    @Test fun restoredSystemCaptionsSelectCaptionLocaleInsteadOfPriorExplicitLanguage() {
        val manager = context.getSystemService(android.view.accessibility.CaptioningManager::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        shadow.setEnabled(true)
        shadow.setLocale(java.util.Locale.GERMAN)
        val text = listOf("fr", "de").map { language ->
            Format.Builder().setId(language).setLanguage(language).setSampleMimeType(MimeTypes.TEXT_VTT)
                .setRoleFlags(C.ROLE_FLAG_CAPTION).build()
        }
        val explicit = trackPreferences(defaults, PlayerSettings(subtitleLanguage = "fr"))
        try {
            assertEquals("fr", selectWithParameters(text, explicit, trackType = C.TRACK_TYPE_TEXT))
            assertEquals("de", selectWithParameters(text, trackPreferences(explicit, PlayerSettings()), trackType = C.TRACK_TYPE_TEXT))
        } finally {
            shadow.setEnabled(false)
            shadow.setLocale(java.util.Locale.ROOT)
        }
    }

    @Test fun languageOrderBeatsFormatAndDescription() {
        val german = format("german", "de", MimeTypes.AUDIO_MPEG_L2)
        val english = format("english", "en", MimeTypes.AUDIO_AC3, C.ROLE_FLAG_DESCRIBES_VIDEO)
        assertEquals("german", select(listOf(german, english), PlayerSettings(audioLanguages = listOf("de", "en"), audioFormat = AudioFormatPreference.PREFER_DOLBY, audioDescription = true)))
        assertEquals("english", select(listOf(german, english), PlayerSettings(audioLanguages = listOf("en", "de"))))
    }

    @Test fun descriptionRoleBeatsFormatButOffPrefersMain() {
        val main = format("main", "de", MimeTypes.AUDIO_AC3)
        val ad = format("ad", "de", MimeTypes.AUDIO_MPEG_L2, C.ROLE_FLAG_DESCRIBES_VIDEO)
        assertEquals("ad", select(listOf(main, ad), PlayerSettings(audioLanguages = listOf("de"), audioFormat = AudioFormatPreference.PREFER_DOLBY, audioDescription = true)))
        assertEquals("main", select(listOf(ad, main), PlayerSettings(audioLanguages = listOf("de"))))
    }

    @Test fun formatOrderBreaksSameLanguageTieWithoutChannelConstraint() {
        val mp2 = format("mp2", "de", MimeTypes.AUDIO_MPEG_L2)
        val ac3 = format("ac3", "de", MimeTypes.AUDIO_AC3)
        val eac3 = format("eac3", "de", MimeTypes.AUDIO_E_AC3)
        assertEquals("eac3", select(listOf(mp2, ac3, eac3), PlayerSettings(audioFormat = AudioFormatPreference.PREFER_DOLBY)))
        assertEquals("mp2", select(listOf(ac3, mp2), PlayerSettings(audioFormat = AudioFormatPreference.PREFER_STEREO)))
    }

    @Test fun explicitOverrideBeatsPreferencesAndUnsupportedTrackIsNotAutomaticallySelected() {
        val german = format("german", "de", MimeTypes.AUDIO_MPEG_L2)
        val english = format("english", "en", MimeTypes.AUDIO_AC3)
        val settings = PlayerSettings(audioLanguages = listOf("de"))
        assertEquals("english", select(listOf(german, english), settings, overrideIndex = 1))
        assertEquals("english", select(listOf(german, english), settings, unsupported = "german"))
    }

    @Test fun nativeDefaultSelectionFlagPrecedesMimePreference() {
        // Media3's implicit content preference is ahead of its MIME preference, not after it.
        val main = format("default-mp2", "de", MimeTypes.AUDIO_MPEG_L2).buildUpon()
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val dolby = format("dolby", "de", MimeTypes.AUDIO_AC3)
        assertEquals("default-mp2", select(listOf(main, dolby), PlayerSettings(audioFormat = AudioFormatPreference.PREFER_DOLBY)))
    }

    @Test fun nativeExplicitOverrideIsNotFilteredByRendererSupport() {
        // The app filters concrete choices and restores against supported tracks. Media3 trusts overrides.
        val supported = format("supported", "de", MimeTypes.AUDIO_MPEG_L2)
        val unsupported = format("unsupported", "en", MimeTypes.AUDIO_AC3)
        assertEquals("unsupported", select(listOf(supported, unsupported), PlayerSettings(), overrideIndex = 1, unsupported = "unsupported"))
    }

    private fun format(id: String, language: String, mime: String, role: Int = 0) = Format.Builder()
        .setId(id).setLanguage(language).setSampleMimeType(mime).setRoleFlags(role)
        .setChannelCount(2).setSampleRate(48000).build()

    private fun select(formats: List<Format>, settings: PlayerSettings, overrideIndex: Int? = null, unsupported: String? = null): String? {
        return selectWithParameters(formats, trackPreferences(defaults, settings), overrideIndex, unsupported)
    }

    private fun selectWithParameters(formats: List<Format>, preferences: TrackSelectionParameters, overrideIndex: Int? = null, unsupported: String? = null, trackType: Int = C.TRACK_TYPE_AUDIO): String? {
        val groups = formats.map { TrackGroup(it.id!!, it) }
        var parameters = preferences
        if (overrideIndex != null) parameters = parameters.buildUpon().addOverride(TrackSelectionOverride(groups[overrideIndex], listOf(0))).build()
        val selector = DefaultTrackSelector(context, parameters)
        selector.init({}, DefaultBandwidthMeter.Builder(context).build())
        val renderer = Proxy.newProxyInstance(RendererCapabilities::class.java.classLoader, arrayOf(RendererCapabilities::class.java)) { _, method, args ->
            when (method.name) {
                "getName" -> "Test renderer"
                "getTrackType" -> trackType
                "supportsFormat" -> RendererCapabilities.create(if ((args!![0] as Format).id == unsupported) C.FORMAT_UNSUPPORTED_SUBTYPE else C.FORMAT_HANDLED)
                "supportsMixedMimeTypeAdaptation" -> RendererCapabilities.ADAPTIVE_NOT_SUPPORTED
                else -> null
            }
        } as RendererCapabilities
        return try {
            selector.selectTracks(arrayOf(renderer), TrackGroupArray(*groups.toTypedArray()), MediaPeriodId(Any()), Timeline.EMPTY).selections[0]?.selectedFormat?.id
        } finally { selector.release() }
    }
}
