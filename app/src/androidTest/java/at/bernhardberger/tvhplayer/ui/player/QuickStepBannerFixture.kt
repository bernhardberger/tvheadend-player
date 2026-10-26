package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision

/** A live quick step on the hidden player, as the screen composes it: the passive Banner's preview timeline. */
@Composable
internal fun QuickStepBanner(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
    feedback: String? = null,
    feedbackIsError: Boolean = feedback != null,
    settled: Boolean = true,
    /** The wall-clock second of the live edge; the step's target clock is derived from it. */
    nowSec: Long = 0,
) {
    PlayerChrome(
        mode = PlayerChromeMode.BANNER_STEP,
        content = PlayerChromeContent("", liveInfoBarData(androidx.compose.ui.platform.LocalContext.current.resources, 1, "Fixture TV", null, null, false, 0, "")),
        timeline = PlayerChromeTimeline.Live(
            state, nowSec = nowSec, step = decision, programmeWindow = programmeWindow,
            feedback = feedback, feedbackIsError = feedbackIsError, stepSettled = settled,
        ),
        actions = PlayerChromeActions(active = false),
        onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
        modifier = modifier,
    )
}
