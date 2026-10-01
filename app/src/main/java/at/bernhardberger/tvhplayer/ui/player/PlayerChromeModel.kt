package at.bernhardberger.tvhplayer.ui.player

import at.bernhardberger.tvheadend.sdk.core.ArtworkId
import at.bernhardberger.tvheadend.sdk.core.EpgEvent
import at.bernhardberger.tvhplayer.core.GlanceBadge
import at.bernhardberger.tvhplayer.core.PlayerStateCell
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.LivePauseAvailability
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision

/**
 * What the player chrome shows. The Banner is passive (header, info bar and timeline, nothing
 * focusable); a quick step on the hidden player shows its preview in the Banner; the controls add
 * the focusable timeline and the action row.
 */
internal enum class PlayerChromeMode { HIDDEN, BANNER, BANNER_STEP, CONTROLS }

internal val PlayerChromeMode.isBanner: Boolean
    get() = this == PlayerChromeMode.BANNER || this == PlayerChromeMode.BANNER_STEP

/** The controls win over the Banner; the Banner shows a quick step's preview while one is showing. */
internal fun playerChromeMode(controls: Boolean, banner: Boolean, stepPreview: Boolean): PlayerChromeMode = when {
    controls -> PlayerChromeMode.CONTROLS
    banner && stepPreview -> PlayerChromeMode.BANNER_STEP
    banner -> PlayerChromeMode.BANNER
    else -> PlayerChromeMode.HIDDEN
}

/** The header's clock, the info bar's identity and programme, and the bar row's state cell. */
internal data class PlayerChromeContent(
    val clock: String,
    val info: PlayerInfoBarData,
    /** What playback is doing: the bar row's state cell, always the real state, also during a step. */
    val state: PlayerStateCell = PlayerStateCell.PLAYING,
    /** The watched channel or recording is being recorded now: the info bar's REC badge. */
    val recordingNow: Boolean = false,
    val badges: List<GlanceBadge> = emptyList(),
    val picon: ArtworkId? = null,
    /** The programme's artwork, the identity card's backdrop. */
    val artwork: String? = null,
    /** The channel whose colour is the identity card's backdrop without [artwork]. */
    val channelId: at.bernhardberger.tvheadend.sdk.core.ChannelId? = null,
)

/** Facts the chrome's timeline shows. */
internal sealed interface PlayerChromeTimeline {
    /**
     * Live TV: the programme window, the timeshift range with its rewindable span and live edge,
     * or the airing progress without timeshift.
     */
    data class Live(
        /** The state the timeline shows: projected to the seek target while [previewing]. */
        val timeshift: AppTimeshiftState,
        val nowSec: Long,
        /** The broadcast airing now, for the schedule progress without a seekable timeshift. */
        val programme: EpgEvent? = null,
        /** The state playback is committed to. */
        val committedTimeshift: AppTimeshiftState = timeshift,
        /**
         * Timeshift is available or its grant is not decided yet, such as while a zap tunes: without a
         * programme the bar then keeps the half-hour slot as its axis.
         */
        val timeshiftExpected: Boolean = committedTimeshift.available,
        val committedWindow: ProgrammeWindow? = null,
        /** The programme window at the shown position. */
        val programmeWindow: ProgrammeWindow? = null,
        /** A seek target is pending or dispatched. */
        val previewing: Boolean = false,
        /** The quick step the Banner previews in [PlayerChromeMode.BANNER_STEP]. */
        val step: TimeshiftSeekDecision? = null,
        /** The pending or dispatched seek's net movement since its step sequence began. */
        val stepDeltaMs: Long? = null,
        /**
         * A live start that has had no pause or timeshift command since, such as right after a zap: it
         * plays the live edge, whether its timing is known yet or not.
         */
        val liveStart: Boolean = false,
        /** The channel plays; false after a failed tune. */
        val liveAvailable: Boolean = true,
        val feedback: String? = null,
        val feedbackIsError: Boolean = feedback != null,
        /** Live-request identity for held rendering and motion, stable across axes within a request. */
        val motionKey: Any? = null,
        /** Tuning, no playback position yet: the bar may show the current schedule instead. */
        val tuning: Boolean = false,
    ) : PlayerChromeTimeline

    /**
     * A recording: elapsed position and length around the bar with its markers, or the elapsed
     * position alone while the length is unknown.
     */
    data class Recording(
        /** The playback position. The elapsed label always shows it, never a step's target. */
        val positionMs: Long,
        /** The recorded length; null while unknown. */
        val durationMs: Long?,
        /** Where a growing recording's bar ends, never before [durationMs]. */
        val displayDurationMs: Long? = durationMs,
        /** Still recording: the length grows and carries the recording mark. */
        val growing: Boolean = false,
        val canSeek: Boolean = false,
        /** A quick step's or the timeline's pending or dispatched target; the bar follows it. */
        val targetMs: Long? = null,
        /** Where the pending step started, for the announced delta. */
        val originMs: Long? = null,
        val markers: List<Long> = emptyList(),
        val markerRevision: Long = 0L,
        val motionKey: Any? = null,
    ) : PlayerChromeTimeline
}

/** The action row's actions and where focus goes. */
internal data class PlayerChromeActions(
    /** The controls own focus: revealed and not covered by a panel. */
    val active: Boolean,
    val paused: Boolean = false,
    /**
     * Live Pause state from the runtime: Pause is dimmed with a reason when it cannot pause. Null
     * (recordings, presentation fixtures) leaves Pause as it is.
     */
    val livePause: LivePauseAvailability? = null,
    /** Record stands before Settings at the row's end. */
    val record: Boolean = true,
    /** Test tag of the control that takes focus back, such as after a panel closes. */
    val restoreFocus: String? = null,
)
