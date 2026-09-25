@file:androidx.media3.common.util.UnstableApi

package at.bernhardberger.tvhplayer.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import at.bernhardberger.tvheadend.sdk.core.SessionObservation
import at.bernhardberger.tvheadend.sdk.media3.LivePlaybackObservation
import at.bernhardberger.tvheadend.sdk.media3.LiveTimeshiftState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

internal fun sessionPlaybackCommands(target: AppPlaybackTarget?, timeshift: Boolean, seekable: Boolean): Player.Commands {
    if (target == null) return Player.Commands.EMPTY
    return Player.Commands.Builder().addAll(
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_METADATA,
        Player.COMMAND_GET_TIMELINE,
    ).apply {
        if (target is AppPlaybackTarget.Recording || timeshift) add(Player.COMMAND_PLAY_PAUSE)
        if (target is AppPlaybackTarget.Recording && seekable) add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
    }.build()
}

internal fun sessionPlaybackMetadata(
    target: AppPlaybackTarget?, observation: SessionObservation, now: Instant,
): MediaMetadata {
    val channel: String?
    val title: String?
    when (target) {
        is AppPlaybackTarget.Live -> {
            channel = observation.channel(target.channelId)?.name
            title = observation.eventAt(target.channelId, now)?.title?.takeIf { it.isNotBlank() } ?: channel
        }
        is AppPlaybackTarget.Recording -> {
            val recording = observation.dvrEntry(target.recordingId)
            channel = recording?.channelName
            title = recording?.title
        }
        null -> return MediaMetadata.EMPTY
    }
    return MediaMetadata.Builder().setTitle(title).setArtist(channel).setSubtitle(channel).build()
}

/** Read-only presentation plus narrowly admitted runtime commands. Never owns the raw player. */
class SessionPlaybackPlayer(private val runtime: AppPlaybackRuntime) : ForwardingSimpleBasePlayer(runtime.player) {
    private var metadata = MediaMetadata.EMPTY
    private var metadataTarget: AppPlaybackTarget? = null
    private var itemUid = Any()
    private val pendingCommands = mutableSetOf<Job>()
    private var closed = false

    /** The activity starts this on the player's looper and cancels it before releasing the session. */
    suspend fun observe(observation: StateFlow<SessionObservation>) {
        val clock = flow {
            while (true) {
                emit(Clock.System.now())
                delay(1_000)
            }
        }
        combine(runtime.activeTarget, runtime.livePlaybackObservation, observation, clock) { target, live, snapshot, now ->
            Triple(target, (live as? LivePlaybackObservation.Active)?.timeshiftState is LiveTimeshiftState.Available,
                sessionPlaybackMetadata(target, snapshot, now))
        }.distinctUntilChanged().collect { (target, _, value) ->
            if (!closed) {
                if (metadataTarget != target) itemUid = Any()
                metadataTarget = target
                metadata = value
                invalidateState()
            }
        }
    }

    override fun getState(): State {
        val target = runtime.activeTarget.value
        if (closed || target == null) return State.Builder().build()
        val timeshift = (runtime.livePlaybackObservation.value as? LivePlaybackObservation.Active)
            ?.timeshiftState is LiveTimeshiftState.Available
        val seekable = target is AppPlaybackTarget.Recording && player.isCurrentMediaItemSeekable &&
            player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        // Publish a sanitized single-item timeline, never the SDK's URI, IDs, extras or raw errors.
        val currentMetadata = metadata.takeIf { metadataTarget == target } ?: MediaMetadata.EMPTY
        val item = MediaItemData.Builder(itemUid)
            .setMediaItem(MediaItem.Builder().setMediaMetadata(currentMetadata).build())
            .setMediaMetadata(currentMetadata)
            .setIsSeekable(seekable)
            .setDurationUs(if (player.duration == C.TIME_UNSET) C.TIME_UNSET else player.duration * 1_000)
            .build()
        return super.getState().buildUpon()
            .setAvailableCommands(sessionPlaybackCommands(target, timeshift, seekable))
            .setPlaylist(listOf(item))
            .setPlaylistMetadata(MediaMetadata.EMPTY)
            .setCurrentMediaItemIndex(0)
            .setPlayerError(null)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> =
        awaitCommand(runtime.setSessionPlayWhenReady(playWhenReady))

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> =
        awaitCommand(runtime.seekRecordingFromSession(positionMs))

    private fun awaitCommand(job: Job): ListenableFuture<*> {
        val result = SettableFuture.create<Void>()
        pendingCommands += job
        job.invokeOnCompletion { failure ->
            pendingCommands -= job
            if (failure == null) result.set(null) else result.cancel(false)
        }
        return result
    }

    /** Release is local lifecycle ownership, never an advertised controller command. */
    fun close() {
        if (closed) return
        closed = true
        pendingCommands.toList().forEach { it.cancel() }
        // ForwardingSimpleBasePlayer owns a private listener. Swapping detaches it without
        // releasing the app-scoped ExoPlayer (which must survive activity stop/start).
        setPlayer(object : SimpleBasePlayer(applicationLooper) {
            override fun getState(): State = State.Builder().build()
        })
    }
}
