package at.bernhardberger.tvhplayer.player

import android.content.Context
import androidx.media3.common.Player
import at.bernhardberger.tvhplayer.core.RecordingPlaybackIntent
import at.bernhardberger.tvhplayer.core.SubscriptionFailureKind
import at.bernhardberger.tvhplayer.core.TimeshiftSeekDecision
import at.bernhardberger.tvhplayer.core.TimeshiftState
import at.bernhardberger.tvhplayer.htsp.DvrEntry
import kotlinx.coroutines.flow.StateFlow

data class PlaybackPreferences(
    val profile: String = "",
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val timeshiftEnabled: Boolean = true,
    val refreshRateMatchingEnabled: Boolean = true,
)

fun interface PlaybackPreferencesProvider {
    suspend fun currentPreferences(): PlaybackPreferences
}

fun createMedia3PlaybackRuntime(
    context: Context,
    client: at.bernhardberger.tvhplayer.htsp.TvheadendClient,
    preferencesProvider: PlaybackPreferencesProvider,
): PlaybackRuntime = Media3PlaybackRuntime(
    context = context,
    client = client,
    preferencesProvider = preferencesProvider,
)

/**
 * Application-owned Media3 playback runtime.
 *
 * The returned [player] is borrowed for Media3 UI, tracks, safe play/pause/seek commands,
 * and surface attachment. Callers must not replace its media sources or release it.
 */
interface PlaybackRuntime {
    val player: Player
    val state: StateFlow<PlaybackSessionState>
    val activeServiceId: StateFlow<Int?>
    val playingLiveServiceId: StateFlow<Int?>
    val activeRecordingId: StateFlow<Int?>
    val timeshiftState: StateFlow<TimeshiftState>
    val liveSubscriptionFailure: StateFlow<SubscriptionFailureKind?>
    val recordingProgressSyncState: StateFlow<RecordingProgressSyncState>
    val diagnostics: StateFlow<PlaybackDiagnosticsSnapshot>

    suspend fun playLive(serviceId: Int): Boolean
    suspend fun playRecording(
        entry: DvrEntry,
        intent: RecordingPlaybackIntent = RecordingPlaybackIntent.DefaultPolicy,
    )

    suspend fun stop()
    suspend fun retryLive(): Boolean
    suspend fun retryRecording(): Boolean
    suspend fun pauseTimeshift(): Boolean
    suspend fun resumeTimeshift(): Boolean
    suspend fun seekTimeshift(deltaMs: Long): TimeshiftSeekDecision?
    suspend fun goLive(): TimeshiftSeekDecision?
    fun recordingPaused()
    fun recordingSeekSettled()
    suspend fun setRefreshRateMatchingEnabled(enabled: Boolean)
    fun setDiagnosticsEnabled(enabled: Boolean)
    suspend fun release()
}
