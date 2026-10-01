package at.bernhardberger.tvhplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import at.bernhardberger.tvhplayer.playback.AppTimeshiftState
import at.bernhardberger.tvhplayer.playback.TimeshiftSeekDecision
import coil3.ImageLoader

/** A live quick step on the hidden player, as the screen composes it: the passive Banner's preview timeline. */
@Composable
internal fun QuickStepBanner(
    state: AppTimeshiftState,
    decision: TimeshiftSeekDecision,
    modifier: Modifier = Modifier,
    programmeWindow: ProgrammeWindow? = null,
    feedback: String? = null,
    feedbackIsError: Boolean = feedback != null,
) {
    PlayerChrome(
        mode = PlayerChromeMode.BANNER_STEP,
        content = PlayerChromeContent("", liveInfoBarData(1, "Fixture TV", null, null, false, 0, "")),
        timeline = PlayerChromeTimeline.Live(
            state, nowSec = 0, step = decision, programmeWindow = programmeWindow,
            feedback = feedback, feedbackIsError = feedbackIsError,
        ),
        actions = PlayerChromeActions(active = false),
        imageLoader = rememberFixtureImageLoader(),
        currentSession = null,
        onTogglePause = {}, onSeek = {}, onStop = {}, onInfo = {}, onOptions = {}, onInteraction = {},
        modifier = modifier,
    )
}

/** An image loader for chrome fixtures, which show no artwork. */
@Composable
internal fun rememberFixtureImageLoader(): ImageLoader {
    val context = LocalContext.current
    return remember { ImageLoader(context) }
}
