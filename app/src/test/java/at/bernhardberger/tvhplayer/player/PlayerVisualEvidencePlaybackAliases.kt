package at.bernhardberger.tvhplayer.player

internal typealias PlaybackDiagnosticsSource =
    at.bernhardberger.tvhplayer.playback.AppPlaybackSource
internal typealias PlaybackThermalLevel =
    at.bernhardberger.tvhplayer.playback.AppPlaybackThermalLevel
internal typealias PlaybackOutputMode =
    at.bernhardberger.tvhplayer.playback.AppPlaybackOutputMode
internal typealias PlaybackSystemDiagnostics =
    at.bernhardberger.tvhplayer.playback.AppPlaybackSystemDiagnostics
internal typealias PlaybackFormatDiagnostics =
    at.bernhardberger.tvhplayer.playback.AppPlaybackFormatDiagnostics
internal typealias PlaybackDiagnosticsSnapshot =
    at.bernhardberger.tvhplayer.playback.AppPlaybackDiagnostics

internal object PlaybackSessionState {
    val Playing: at.bernhardberger.tvhplayer.playback.AppPlaybackState =
        at.bernhardberger.tvhplayer.playback.AppPlaybackState.Playing

    fun Recovering(
        retryDelayMillis: Long,
    ): at.bernhardberger.tvhplayer.playback.AppPlaybackState =
        at.bernhardberger.tvhplayer.playback.AppPlaybackState.Recovering(retryDelayMillis)
}
