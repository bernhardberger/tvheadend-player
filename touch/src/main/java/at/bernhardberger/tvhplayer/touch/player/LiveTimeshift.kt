package at.bernhardberger.tvhplayer.touch.player

import at.bernhardberger.tvhplayer.notices.Notice
import at.bernhardberger.tvhplayer.notices.NoticeCenter
import at.bernhardberger.tvhplayer.notices.PauseUnavailableReason
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import at.bernhardberger.tvheadend.sdk.core.ChannelId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftCommandDisposition
import at.bernhardberger.tvheadend.sdk.media3.TimeshiftTimeline
import at.bernhardberger.tvhplayer.playback.AppPlaybackRuntime
import at.bernhardberger.tvhplayer.playback.AppPlaybackTarget
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

/**
 * The player's timeshift: samples the runtime into the bar and sends the transport's commands.
 * Commands run in [scope], the screen's; the runtime serializes them against each other.
 */
internal class LiveTimeshift(private val runtime: AppPlaybackRuntime, private val scope: CoroutineScope) {
    private val bar = mutableStateOf<LiveTimeline?>(null)
    private val shown = mutableStateOf(ShownPrograms(null, null))
    private var sample = AppTimeshiftState()
    private var held: TimeshiftTimeline? = null

    /** Read only by what draws the bar. */
    val timeline: State<LiveTimeline?> get() = bar

    /** The program the viewer is in and the one after it, by the TV's rule ([shownPrograms]). */
    val programs: State<ShownPrograms> get() = shown

    /** Samples every [intervalMs] while [channelId] is the runtime's target; another target samples nothing. */
    suspend fun follow(
        channelId: ChannelId,
        intervalMs: Long,
        liveStart: () -> Boolean,
        eventAt: (Instant) -> EpgEvent?,
        nextAt: (Instant) -> EpgEvent?,
    ) {
        while (true) {
            val raw = if (runtime.activeTarget.value == AppPlaybackTarget.Live(channelId)) {
                runtime.sampleTimeshiftPresentation()
            } else AppTimeshiftState()
            held = heldHistoryStart(held, raw)
            sample = raw.copy(historyStartTimeline = held)
            val nowSec = System.currentTimeMillis() / MS_PER_SECOND
            bar.value = liveTimeline(sample, nowSec, ZoneId.systemDefault(), eventAt)
            shown.value = shownPrograms(sample, liveStart(), Instant.fromEpochSeconds(nowSec), eventAt, nextAt)
            delay(intervalMs)
        }
    }

    /** Paused resumes, the TV's way: a resume the server refuses leaves the player paused. */
    fun toggle(paused: Boolean) {
        scope.launch {
            if (!paused) runtime.pauseTimeshiftPlayback()
            else if (runtime.resumeTimeshift().disposition == TimeshiftCommandDisposition.NOT_ACCEPTED) runtime.pause()
        }
    }

    fun skip(deltaMs: Long) = move(skipMove(sample, deltaMs))

    /** The drag let go at [target] seconds into the program. */
    fun seek(target: Int) {
        move(dragMove(bar.value ?: return, target))
    }

    fun goLive() = move(TimeshiftMove.GoLive)

    private fun move(move: TimeshiftMove) {
        val from = sample
        scope.launch {
            when (move) {
                TimeshiftMove.None -> Unit
                TimeshiftMove.GoLive -> {
                    runtime.goLive()
                    runtime.play()
                }
                is TimeshiftMove.By -> {
                    val anchor = from.playbackTarget ?: return@launch
                    from.timeline?.resolveSelection(anchor, delta = move.deltaMs.milliseconds)
                        ?.let { runtime.seekTimeshift(it) }
                }
            }
        }
    }
}

/**
 * The player's own play/pause intent, which the live pause state can override. Read again once the
 * listener is in place, as the TV's `rememberPlaybackIntent` does: a change between composition and
 * the listener would otherwise never arrive.
 */
@Composable
internal fun rememberPlayWhenReady(player: Player): State<Boolean> = produceState(player.playWhenReady, player) {
    val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            value = playWhenReady
        }
    }
    player.addListener(listener)
    value = player.playWhenReady
    awaitDispose { player.removeListener(listener) }
}

/**
 * A pause the runtime dropped because the channel turned out to have no timeshift. The touch
 * design has no notice for it yet: the transport goes away with the channel's pause.
 */
@Composable
internal fun ConsumePauseNotices(runtime: AppPlaybackRuntime, notices: NoticeCenter) {
    val notice by runtime.livePauseNotice.collectAsStateWithLifecycle()
    LaunchedEffect(notice) {
        // A pause asked for while timeshift was starting was dropped: no grant for this channel, as the TV says.
        notice?.let {
            notices.post(Notice.PauseUnavailable(PauseUnavailableReason.CHANNEL_UNAVAILABLE), notices.context())
            runtime.consumeLivePauseNotice(it)
        }
    }
}

/** Samples at the TV player's cadence while the bar shows and moves; the thin line and a paused bar need less. */
@Composable
internal fun FollowTimeshift(
    timeshift: LiveTimeshift,
    channelId: ChannelId,
    fast: Boolean,
    liveStart: Boolean,
    eventAt: (Instant) -> EpgEvent?,
    nextAt: (Instant) -> EpgEvent?,
) {
    val interval = if (fast) FAST_SAMPLE_MS else SLOW_SAMPLE_MS
    val latestLiveStart by rememberUpdatedState(liveStart)
    val latestEventAt by rememberUpdatedState(eventAt)
    val latestNextAt by rememberUpdatedState(nextAt)
    LaunchedEffect(timeshift, channelId, interval) {
        timeshift.follow(channelId, interval, { latestLiveStart }, { latestEventAt(it) }, { latestNextAt(it) })
    }
}

private const val FAST_SAMPLE_MS = 250L
private const val SLOW_SAMPLE_MS = 1_000L
