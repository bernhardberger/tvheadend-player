package at.bernhardberger.tvhplayer.touch.player

import android.content.res.Configuration
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.core.TvheadendSession
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.stores.LastPlayedChannelStore
import at.bernhardberger.tvhplayer.stores.recentChannelToPersist
import at.bernhardberger.tvhplayer.viewmodels.ChannelScopeState

@Composable
internal fun PlayerEffects(
    timeshift: LiveTimeshift,
    shown: ChannelId,
    fast: Boolean,
    liveStart: Boolean,
    session: TvheadendSession,
    runtime: AppPlaybackRuntime,
    notices: NoticeCenter,
    scope: ChannelScopeState,
    observation: SessionObservation,
    status: LivePlayerStatus,
    lastPlayed: LastPlayedChannelStore,
) {
    val confirmed = recentChannelToPersist(
        scope.profileId, shown.takeIf { status == LivePlayerStatus.Playing },
        observation.currentSession != null && scope.observation?.currentSession === observation.currentSession,
    )
    LaunchedEffect(confirmed) {
        confirmed?.let { (profile, channel) -> lastPlayed.setChannelId(profile, channel) }
    }
    FollowTimeshift(
        timeshift, shown, fast, liveStart,
        eventAt = { session.observation.value.eventAt(shown, it) },
        nextAt = { session.observation.value.nextEvent(shown, it) },
    )
    ConsumePauseNotices(runtime, notices)
}

/**
 * Landscape plays full screen without the system bars (the drawings tablet-player and
 * phone-player-landscape); a swipe from the edge shows them for a moment. Upright keeps them
 * (phone-player, tablet-upright-player).
 */
@Composable
internal fun SystemBarsForOrientation(window: Window, inWindow: Boolean) {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(window, landscape, inWindow) {
        if (inWindow) return@LaunchedEffect
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val systemBars = WindowInsetsCompat.Type.systemBars()
        if (landscape) bars.hide(systemBars) else bars.show(systemBars)
    }
}
