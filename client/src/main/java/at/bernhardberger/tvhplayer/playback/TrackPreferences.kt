package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.MimeTypes
import at.bernhardberger.tvhplayer.settings.AudioFormatPreference
import at.bernhardberger.tvhplayer.settings.PlayerSettings
import at.bernhardberger.tvhplayer.settings.normalizeAudioLanguages

/**
 * Only preference-owned fields change. Explicit overrides and interruption-owned flags survive.
 *
 * Media3 ranks automatic audio candidates by renderer support, preferred language, preferred role
 * flags, labels, DEFAULT selection flag, main-or-no-role, device-locale match, constraints, then
 * preferred MIME order. Format is only a tie-breaker for otherwise equal tracks; without a matching
 * preferred language, the device locale wins over format. Explicit overrides bypass this ranking.
 */
fun trackPreferences(
    current: TrackSelectionParameters,
    settings: PlayerSettings,
): TrackSelectionParameters = current.buildUpon()
    .setPreferredAudioLanguages(*normalizeAudioLanguages(settings.audioLanguages).toTypedArray())
    .setPreferredAudioMimeTypes(*when (settings.audioFormat) {
        AudioFormatPreference.AUTOMATIC -> emptyArray()
        AudioFormatPreference.PREFER_DOLBY -> arrayOf(MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_AC3)
        AudioFormatPreference.PREFER_STEREO -> arrayOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG_L2, MimeTypes.AUDIO_MPEG)
    })
    .setPreferredAudioRoleFlags(if (settings.audioDescription) C.ROLE_FLAG_DESCRIBES_VIDEO else 0)
    .apply {
        val language = settings.subtitleLanguage
        if (language == null) {
            setPreferredTextLanguageAndRoleFlagsToCaptioningManagerSettings()
        } else {
            setPreferredTextLanguages(language)
            setPreferredTextRoleFlags(0)
        }
    }
    .build()
