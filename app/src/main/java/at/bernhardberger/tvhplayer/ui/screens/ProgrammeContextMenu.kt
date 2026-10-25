package at.bernhardberger.tvhplayer.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import at.bernhardberger.tvheadend.sdk.core.*
import at.bernhardberger.tvhplayer.core.*
import at.bernhardberger.tvhplayer.ui.common.isRunning
import at.bernhardberger.tvhplayer.ui.common.progress
import at.bernhardberger.tvhplayer.ui.common.programmeEpisodeLine
import at.bernhardberger.tvhplayer.ui.components.*

/** One programme menu for the opening programme and every related airing. */
@OptIn(at.bernhardberger.tvheadend.sdk.playback.ExperimentalTvheadendApi::class)
@Composable
internal fun ProgrammeContextMenu(
    event: EpgEvent,
    channel: Channel?,
    recording: DvrEntry?,
    fromGuide: Boolean,
    stacked: Boolean,
    nowSec: Long,
    canModify: Boolean,
    currentSession: CurrentSessionObservation?,
    observation: SessionObservation,
    canPlayRecording: Boolean,
    onAction: (EpgEvent, BrowseContextAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
) {
    // The recording plays only as details would play it: admitted, with a session and a player route.
    val admission = recording?.takeIf { canPlayRecording && currentSession != null }
        ?.let(observation::recordingPlaybackAdmission)
    val actions = channelContextActions(event, nowSec, recording, admission, canModify)
    val indicator = watchingIndicator(event, nowSec)
    val showGuide = BrowseContextAction.SHOW_GUIDE in programmeDetailsActions(event, nowSec, recording, null,
        canModify, channel != null && currentSession != null, fromGuide, stacked = stacked)
    BrowseContextMenu(
        title = event.title.orEmpty(),
        channel = channelTitleText(channel?.number, channel?.name.orEmpty()),
        episode = programmeEpisodeLine(event.episode, event.subtitle, LocalContext.current.resources),
        time = programmeAiringTime(event, nowSec),
        summary = event.summary?.takeIf(String::isNotBlank) ?: event.description.orEmpty(),
        actions = actions.filter { it != BrowseContextAction.SHOW_GUIDE } +
            listOfNotNull(BrowseContextAction.SHOW_GUIDE.takeIf { showGuide }),
        watchChannel = !event.isRunning(nowSec), programDetailsLabel = !fromGuide,
        modifier = modifier, onAction = { onAction(event, it) }, onClose = onClose, backEnabled = backEnabled,
        recordingState = recording?.state,
        playbackIndicator = indicator,
        artwork = {
            // The menu shows its pictures with the session it was opened with.
            ProvideArtworkSession(currentSession, observation) {
                ProgrammeArtwork(event.image, channel?.icon, event.progress(nowSec).takeIf { event.isRunning(nowSec) },
                    Modifier.fillMaxSize().testTag(if (event.image != null && currentSession != null)
                        "browse-context-artwork" else "browse-context-picon"),
                    plateModifier = Modifier.testTag("browse-context-plate"),
                    progressModifier = Modifier.testTag("browse-context-progress"),
                    progressColor = progressColor(indicator != ChannelPlaybackIndicator.NONE))
            }
        },
    )
}
