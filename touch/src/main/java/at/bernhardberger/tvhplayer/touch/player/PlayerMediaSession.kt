package at.bernhardberger.tvhplayer.touch.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import at.bernhardberger.tvheadend.sdk.core.Channel
import at.bernhardberger.tvheadend.sdk.core.ContentGenre
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvhplayer.core.programmeCategory
import at.bernhardberger.tvheadend.sdk.core.hasChannelNumber
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.SessionMetadataLabels
import at.bernhardberger.tvhplayer.playback.SessionPlaybackErrorKind
import at.bernhardberger.tvhplayer.playback.SessionPlaybackPlayer
import at.bernhardberger.tvhplayer.ui.common.programmeCategoryLabelRes
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import at.bernhardberger.tvhplayer.presentation.R as SharedR

/**
 * The player's media session, which gives the picture-in-picture window its play/pause and
 * channel buttons. It lives while the player activity is started (full screen or in the window),
 * as the TV's activity-only session does: no service, no notification. Play/pause go through the shared session player
 * into the runtime's own pause and resume; previous/next call the channel step the activity
 * registers.
 */
internal class PlayerMediaSession(
    private val activity: ComponentActivity,
    private val runtime: AppPlaybackRuntime,
    private val observation: StateFlow<SessionObservation>,
    private val step: (Int) -> Unit,
) : DefaultLifecycleObserver {
    private val id = UUID.randomUUID().toString()
    private var session: MediaSession? = null
    private var player: SessionPlaybackPlayer? = null
    private var observing: Job? = null
    private var unregisterStep: (() -> Unit)? = null

    @OptIn(UnstableApi::class)
    override fun onStart(owner: LifecycleOwner) {
        if (session != null) return
        // Registered first: the session offers previous/next only while a channel step exists.
        unregisterStep = runtime.registerSessionChannelStep(step)
        val sessionPlayer = SessionPlaybackPlayer(runtime, programmeWindowEnabled = true, TouchSessionLabels(activity))
        player = sessionPlayer
        observing = activity.lifecycleScope.launch { sessionPlayer.observe(observation) }
        val open = PendingIntent.getActivity(
            activity, 0, Intent(activity, PlayerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(activity, sessionPlayer).setId(id).setSessionActivity(open).build()
    }

    override fun onStop(owner: LifecycleOwner) {
        observing?.cancel()
        observing = null
        session?.release()
        session = null
        player?.close()
        player = null
        unregisterStep?.invoke()
        unregisterStep = null
    }
}

/** The TV's labels (`AppSessionMetadataLabels`), from the shared category and error texts. */
private class TouchSessionLabels(private val context: Context) : SessionMetadataLabels {
    override fun genre(genre: ContentGenre): String? =
        programmeCategory(genre)?.let { context.getString(programmeCategoryLabelRes(it)) }

    override fun channelNumber(channel: Channel): String? =
        channel.number?.takeIf { channel.hasChannelNumber }?.let { number ->
            "$number" + (channel.numberMinor?.takeIf { it > 0 }?.let { ".$it" } ?: "")
        }

    override fun error(kind: SessionPlaybackErrorKind): String = context.getString(
        when (kind) {
            SessionPlaybackErrorKind.RECORDING -> SharedR.string.error_306_title
            SessionPlaybackErrorKind.CHANNEL_UNAVAILABLE -> SharedR.string.error_203_title
            SessionPlaybackErrorKind.CHANNEL -> SharedR.string.error_210_title
        },
    )
}
