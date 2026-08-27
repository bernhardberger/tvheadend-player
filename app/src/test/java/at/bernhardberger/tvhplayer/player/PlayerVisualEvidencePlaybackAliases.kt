package at.bernhardberger.tvhplayer.player

internal typealias PlaybackDiagnosticsSource =
    at.bernhardberger.tvhplayer.playback.AppPlaybackSource
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
